package com.haodaone.announcement.service;

import com.haodaone.announcement.entity.Announcement;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnouncementAudienceTest {

    private static Announcement announcement(Long id, boolean pinned, LocalDateTime publishedAt) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setPinned(pinned);
        announcement.setPublishedAt(publishedAt);
        return announcement;
    }

    @Test
    void sanitizeStripsHtmlAndControlCharactersButKeepsLineBreaksAndLinks() {
        String dirty = "<script>alert('x')</script>Hello <b>team</b>,\r\nSee https://example.com/policy?a=1&b=2\u0007\n\n<img src=x onerror=alert(1)>Thanks";
        String clean = AnnouncementAudience.sanitize(dirty);

        assertThat(clean).doesNotContain("<").doesNotContain(">").doesNotContain("\u0007").doesNotContain("\r");
        assertThat(clean).contains("Hello team,\nSee https://example.com/policy?a=1&b=2");
        assertThat(clean).endsWith("Thanks");
    }

    @Test
    void sanitizeLeavesComparisonsThatAreNotTagsAlone() {
        assertThat(AnnouncementAudience.sanitize("Targets: 5 < 10 and 10 > 5")).isEqualTo("Targets: 5 < 10 and 10 > 5");
    }

    @Test
    void departmentAudienceOnlyIncludesListedDepartments() {
        Announcement all = new Announcement();
        Announcement sales = new Announcement();
        sales.setAudienceType(Announcement.AUDIENCE_DEPARTMENTS);
        sales.setAudienceDepartmentIds("3, 5");

        assertThat(AnnouncementAudience.includes(all, null)).isTrue();
        assertThat(AnnouncementAudience.includes(sales, 5L)).isTrue();
        assertThat(AnnouncementAudience.includes(sales, 4L)).isFalse();
        assertThat(AnnouncementAudience.includes(sales, null)).isFalse();

        List<AnnouncementAudience.Member> members = List.of(
                new AnnouncementAudience.Member(1L, 11L, 3L),
                new AnnouncementAudience.Member(2L, 12L, 4L),
                new AnnouncementAudience.Member(3L, 13L, null));
        assertThat(AnnouncementAudience.audienceOf(sales, members))
                .extracting(AnnouncementAudience.Member::employeeId).containsExactly(1L);
        assertThat(AnnouncementAudience.audienceOf(all, members)).hasSize(3);
    }

    @Test
    void parseIdsToleratesJunk() {
        assertThat(AnnouncementAudience.parseIds("1,,x, 2 ,1")).containsExactly(1L, 2L);
        assertThat(AnnouncementAudience.parseIds(null)).isEmpty();
    }

    @Test
    void feedShowsPinnedFirstThenNewest() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 9, 0);
        List<Announcement> items = new ArrayList<>(List.of(
                announcement(1L, false, base),
                announcement(2L, true, base.minusDays(5)),
                announcement(3L, false, base.plusDays(2)),
                announcement(4L, true, base.plusDays(1))));

        items.sort(AnnouncementService.feedOrder());

        assertThat(items).extracting(Announcement::getId).containsExactly(4L, 2L, 3L, 1L);
    }
}
