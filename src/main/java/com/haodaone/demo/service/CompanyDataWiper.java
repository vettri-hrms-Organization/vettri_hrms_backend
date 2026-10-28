package com.haodaone.demo.service;

import com.haodaone.company.entity.Company;
import jakarta.persistence.EntityManager;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hard-deletes every row that belongs to one company, leaving the company row itself.
 * Used only to reset sales demo workspaces (see DemoCompanyService) - never for customer data.
 *
 * <p>Generic on purpose so new modules are wiped without touching this class: it walks the JPA
 * metamodel, finds every entity that reaches {@link Company} through owning many-to-one /
 * one-to-one associations (directly, e.g. {@code employee.company}, or transitively, e.g.
 * {@code candidate.jobOpening.company}), collects the ids of the company's rows, nulls nullable
 * references that would otherwise block the delete order (self references, cycles) and then
 * bulk-deletes entity by entity, children first. Hibernate 6 bulk deletes also clear
 * many-to-many / element-collection tables (user_roles, holiday_location, ...).
 *
 * <p>Plain-JDBC tables (no entity) and the few FK columns mapped as raw Long ids are listed
 * explicitly below - add to those lists if a new JDBC-only table references company data.
 */
@Component
public class CompanyDataWiper {

    private static final Logger log = LoggerFactory.getLogger(CompanyDataWiper.class);
    private static final int CHUNK = 500;

    /**
     * JDBC-managed tables (no entity) that reference company data: {table, delete statement with one
     * company id parameter}. Run first, in this order, when the table exists.
     */
    private static final List<String[]> JDBC_DELETES = List.of(
            new String[]{"assistant_pending_leave_action", "DELETE FROM assistant_pending_leave_action WHERE company_id = ?"},
            // assistant_message rows cascade (ON DELETE CASCADE)
            new String[]{"assistant_conversation", "DELETE FROM assistant_conversation WHERE company_id = ?"},
            new String[]{"notification", "DELETE FROM notification WHERE company_id = ?"},
            new String[]{"notification_email_preference", "DELETE FROM notification_email_preference WHERE company_id = ?"},
            new String[]{"support_request", "DELETE FROM support_request WHERE company_id = ?"},
            new String[]{"employee_asset", "DELETE FROM employee_asset WHERE company_id = ?"},
            new String[]{"payroll_ledger_settings", "DELETE FROM payroll_ledger_settings WHERE company_id = ?"},
            new String[]{"account_token", "DELETE FROM account_token WHERE user_id IN (SELECT id FROM app_user WHERE company_id = ?)"});

    /** Entity attributes that hold a raw FK id (no JPA association) and must be cleared before deleting. */
    private static final Map<String, List<String>> UNMAPPED_FK_ATTRIBUTES = Map.of(
            "Candidate", List.of("createdEmployeeId"),
            "Employee", List.of("biometricDeviceId", "officeLocationId"),
            "OvertimeEntry", List.of("shiftId"));

    /** Extra "delete A before B" rules the metamodel cannot see (raw id columns / element collections with FKs). */
    private static final List<String[]> EXTRA_ORDER = List.of(
            new String[]{"Holiday", "OfficeLocation"},
            new String[]{"RemoteSupportCredential", "MonitoredDevice"});

    @PersistenceContext
    private EntityManager entityManager;

    private final JdbcTemplate jdbcTemplate;

    public CompanyDataWiper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** One owning reference from an entity to another entity. */
    private record Ref(String attribute, Class<?> target, boolean nullable, boolean collection) {
    }

    private static final class Node {
        final EntityType<?> type;
        final String name;
        final String idAttribute;
        final List<Ref> refs = new ArrayList<>();
        String scopePath;
        List<Object> ids = List.of();

        Node(EntityType<?> type, String idAttribute) {
            this.type = type;
            this.name = type.getName();
            this.idAttribute = idAttribute;
        }
    }

    /**
     * Deletes all of the company's data. Must run inside the caller's transaction.
     * Returns rows deleted per entity (JDBC tables prefixed with "table:").
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Integer> wipe(Long companyId) {
        Map<String, Integer> deleted = new LinkedHashMap<>();
        entityManager.flush();

        for (String[] statement : JDBC_DELETES) {
            if (!tableExists(statement[0])) continue;
            int n = jdbcTemplate.update(statement[1], companyId);
            if (n > 0) deleted.put("table:" + statement[0], n);
        }

        Map<Class<?>, Node> nodes = buildGraph();
        List<Node> scoped = nodes.values().stream()
                .filter(n -> n.scopePath != null)
                .sorted(Comparator.comparing(n -> n.name))
                .toList();
        for (Node node : scoped) {
            node.ids = entityManager.createQuery(
                            "select e." + node.idAttribute + " from " + node.name + " e where e." + node.scopePath + ".id = :cid",
                            Object.class)
                    .setParameter("cid", companyId)
                    .getResultList();
        }
        List<Node> active = scoped.stream().filter(n -> !n.ids.isEmpty()).toList();

        List<String[]> toNull = new ArrayList<>(); // {entityName, attribute}
        List<Node> order = deleteOrder(active, nodes, toNull);

        Map<String, Node> byName = new HashMap<>();
        active.forEach(n -> byName.put(n.name, n));
        for (Node node : active) {
            for (String attribute : UNMAPPED_FK_ATTRIBUTES.getOrDefault(node.name, List.of())) {
                if (hasBasicAttribute(node.type, attribute)) toNull.add(new String[]{node.name, attribute});
            }
        }
        for (String[] nullRef : toNull) {
            Node node = byName.get(nullRef[0]);
            for (List<Object> chunk : chunks(node.ids)) {
                entityManager.createQuery("update " + node.name + " e set e." + nullRef[1] + " = null where e."
                                + node.idAttribute + " in :ids")
                        .setParameter("ids", chunk)
                        .executeUpdate();
            }
        }
        for (Node node : order) {
            int n = 0;
            for (List<Object> chunk : chunks(node.ids)) {
                n += entityManager.createQuery("delete from " + node.name + " e where e." + node.idAttribute + " in :ids")
                        .setParameter("ids", chunk)
                        .executeUpdate();
            }
            deleted.put(node.name, n);
        }
        entityManager.flush();
        entityManager.clear();
        log.info("Wiped company {}: {}", companyId, deleted);
        return deleted;
    }

    // ------------------------------------------------------------------ graph

    private Map<Class<?>, Node> buildGraph() {
        Map<Class<?>, Node> nodes = new LinkedHashMap<>();
        for (EntityType<?> type : entityManager.getMetamodel().getEntities()) {
            if (Company.class.equals(type.getJavaType())) continue;
            String idAttribute;
            try {
                idAttribute = type.getId(type.getIdType().getJavaType()).getName();
            } catch (RuntimeException e) {
                continue; // composite ids are not used by company data
            }
            Node node = new Node(type, idAttribute);
            for (SingularAttribute<?, ?> attribute : type.getSingularAttributes()) {
                Attribute.PersistentAttributeType kind = attribute.getPersistentAttributeType();
                if (kind != Attribute.PersistentAttributeType.MANY_TO_ONE && kind != Attribute.PersistentAttributeType.ONE_TO_ONE) {
                    continue;
                }
                AnnotatedElement element = annotated(attribute.getJavaMember());
                if (kind == Attribute.PersistentAttributeType.ONE_TO_ONE && element != null) {
                    OneToOne oneToOne = element.getAnnotation(OneToOne.class);
                    if (oneToOne != null && !oneToOne.mappedBy().isEmpty()) continue; // inverse side owns no FK
                }
                JoinColumn joinColumn = element == null ? null : element.getAnnotation(JoinColumn.class);
                boolean nullable = attribute.isOptional()
                        && (joinColumn == null || (joinColumn.nullable() && joinColumn.updatable()));
                node.refs.add(new Ref(attribute.getName(), attribute.getJavaType(), nullable, false));
            }
            for (PluralAttribute<?, ?, ?> attribute : type.getPluralAttributes()) {
                if (attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.MANY_TO_MANY) continue;
                AnnotatedElement element = annotated(attribute.getJavaMember());
                ManyToMany manyToMany = element == null ? null : element.getAnnotation(ManyToMany.class);
                if (manyToMany != null && !manyToMany.mappedBy().isEmpty()) continue;
                node.refs.add(new Ref(attribute.getName(), attribute.getElementType().getJavaType(), false, true));
            }
            nodes.put(type.getJavaType(), node);
        }

        // Direct scope: an owning association to Company.
        for (Node node : nodes.values()) {
            for (Ref ref : node.refs) {
                if (!ref.collection() && Company.class.equals(ref.target())) {
                    node.scopePath = ref.attribute();
                    break;
                }
            }
        }
        // Transitive scope, shortest path first (one hop per round).
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<Node, String> found = new LinkedHashMap<>();
            for (Node node : nodes.values()) {
                if (node.scopePath != null) continue;
                for (Ref ref : node.refs) {
                    Node target = ref.collection() ? null : nodes.get(ref.target());
                    if (target != null && target != node && target.scopePath != null) {
                        found.put(node, ref.attribute() + "." + target.scopePath);
                        break;
                    }
                }
            }
            for (Map.Entry<Node, String> entry : found.entrySet()) {
                entry.getKey().scopePath = entry.getValue();
                changed = true;
            }
        }
        return nodes;
    }

    /**
     * Children-first order over the active nodes. A node can be deleted once no other remaining
     * node holds a non-nullable reference to it; nullable references from remaining nodes (and all
     * self references) are recorded in {@code toNull} and cleared before any delete runs.
     */
    private List<Node> deleteOrder(List<Node> active, Map<Class<?>, Node> nodes, List<String[]> toNull) {
        Set<Node> remaining = new LinkedHashSet<>(active);
        Map<String, Node> byName = new HashMap<>();
        active.forEach(n -> byName.put(n.name, n));

        // incoming.get(T) = list of {source node, ref} pointing at T
        Map<Node, List<Object[]>> incoming = new HashMap<>();
        for (Node source : active) {
            for (Ref ref : source.refs) {
                Node target = nodes.get(ref.target());
                if (target == null || !remaining.contains(target)) continue;
                if (target == source) {
                    if (ref.nullable() && !ref.collection()) toNull.add(new String[]{source.name, ref.attribute()});
                    continue;
                }
                incoming.computeIfAbsent(target, k -> new ArrayList<>()).add(new Object[]{source, ref});
            }
        }
        for (String[] rule : EXTRA_ORDER) {
            Node source = byName.get(rule[0]);
            Node target = byName.get(rule[1]);
            if (source != null && target != null) {
                incoming.computeIfAbsent(target, k -> new ArrayList<>())
                        .add(new Object[]{source, new Ref("(extra)", target.type.getJavaType(), false, true)});
            }
        }

        List<Node> order = new ArrayList<>();
        Set<String> nulled = new HashSet<>();
        while (!remaining.isEmpty()) {
            Node pick = null;
            // Prefer a node nothing remaining points at; otherwise one blocked only by nullable refs.
            for (Node candidate : remaining) {
                if (blockers(candidate, incoming, remaining, false) == 0) {
                    pick = candidate;
                    break;
                }
            }
            if (pick == null) {
                int best = Integer.MAX_VALUE;
                for (Node candidate : remaining) {
                    if (blockers(candidate, incoming, remaining, true) > 0) continue;
                    int soft = blockers(candidate, incoming, remaining, false);
                    if (soft < best) {
                        best = soft;
                        pick = candidate;
                    }
                }
                if (pick == null) {
                    throw new IllegalStateException("Cannot order company data deletion: circular non-null references among "
                            + remaining.stream().map(n -> n.name).toList());
                }
                for (Object[] edge : incoming.getOrDefault(pick, List.of())) {
                    Node source = (Node) edge[0];
                    Ref ref = (Ref) edge[1];
                    if (remaining.contains(source) && source != pick && nulled.add(source.name + "." + ref.attribute())) {
                        toNull.add(new String[]{source.name, ref.attribute()});
                    }
                }
            }
            remaining.remove(pick);
            order.add(pick);
        }
        return order;
    }

    /** Number of references from other remaining nodes to {@code target}; hardOnly counts only non-nullable ones. */
    private static int blockers(Node target, Map<Node, List<Object[]>> incoming, Set<Node> remaining, boolean hardOnly) {
        int count = 0;
        for (Object[] edge : incoming.getOrDefault(target, List.of())) {
            Node source = (Node) edge[0];
            Ref ref = (Ref) edge[1];
            if (source == target || !remaining.contains(source)) continue;
            if (hardOnly && ref.nullable() && !ref.collection()) continue;
            count++;
        }
        return count;
    }

    private static AnnotatedElement annotated(Member member) {
        return member instanceof AnnotatedElement element ? element : null;
    }

    private static boolean hasBasicAttribute(EntityType<?> type, String name) {
        try {
            return type.getAttribute(name).getPersistentAttributeType() == Attribute.PersistentAttributeType.BASIC;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?",
                Integer.class, table.toLowerCase(Locale.ROOT));
        return count != null && count > 0;
    }

    private static List<List<Object>> chunks(List<Object> ids) {
        List<List<Object>> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += CHUNK) {
            result.add(ids.subList(i, Math.min(ids.size(), i + CHUNK)));
        }
        return result;
    }
}
