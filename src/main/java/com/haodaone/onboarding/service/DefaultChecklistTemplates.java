package com.haodaone.onboarding.service;

import com.haodaone.onboarding.entity.ChecklistConstants;

import java.util.List;

/** Built-in onboarding and exit checklists seeded for each company on first use. */
public final class DefaultChecklistTemplates {

    public record TaskDef(String title, String description, String ownerType, int dueOffsetDays,
                          boolean requiresDocument, String documentType, String linkKey) {}

    public record TemplateDef(String name, String type, String description, List<TaskDef> tasks) {}

    public static final TemplateDef ONBOARDING = new TemplateDef("Standard onboarding", ChecklistConstants.TYPE_ONBOARDING,
            "Default new-hire checklist. Due dates are relative to the date of joining.", List.of(
            new TaskDef("Submit ID and address proof", "Upload a government ID (Aadhaar / passport / voter ID) that shows your current address.",
                    ChecklistConstants.OWNER_NEW_HIRE, -3, true, "ID_PROOF", null),
            new TaskDef("Share PAN and bank details", "Provide PAN, bank account number, IFSC and bank name for payroll, then verify them in the Bank & statutory card on your profile.",
                    ChecklistConstants.OWNER_NEW_HIRE, 0, false, null, "BANK_DETAILS"),
            new TaskDef("Issue appointment letter", "Generate the appointment letter from the employee's Letters tab.",
                    ChecklistConstants.OWNER_HR, 0, false, null, "APPOINTMENT_LETTER"),
            new TaskDef("Sign appointment letter", "Download your appointment letter, sign it and upload the signed copy.",
                    ChecklistConstants.OWNER_NEW_HIRE, 2, true, "EMPLOYMENT_CONTRACT", "MY_LETTERS"),
            new TaskDef("Create work email account", "Create the official email account and share credentials securely.",
                    ChecklistConstants.OWNER_IT, -1, false, null, null),
            new TaskDef("Provision laptop and accessories", "Prepare, image and hand over the laptop and accessories; record the asset.",
                    ChecklistConstants.OWNER_IT, 0, false, null, null),
            new TaskDef("Add to payroll", "Create the salary structure and verify bank / statutory details before the next payroll run.",
                    ChecklistConstants.OWNER_HR, 3, false, null, "SALARY"),
            new TaskDef("Induction session", "Company overview, policies, leave and attendance walkthrough.",
                    ChecklistConstants.OWNER_HR, 1, false, null, null),
            new TaskDef("Assign an onboarding buddy", "Pick a buddy from the team and introduce them to the new hire.",
                    ChecklistConstants.OWNER_MANAGER, 0, false, null, null),
            new TaskDef("30-day check-in", "Review goals, settling in and any blockers.",
                    ChecklistConstants.OWNER_MANAGER, 30, false, null, null),
            new TaskDef("60-day check-in", "Mid-probation feedback conversation.",
                    ChecklistConstants.OWNER_MANAGER, 60, false, null, null),
            new TaskDef("90-day check-in", "Probation review and confirmation recommendation.",
                    ChecklistConstants.OWNER_MANAGER, 90, false, null, null)));

    public static final TemplateDef EXIT = new TemplateDef("Standard offboarding", ChecklistConstants.TYPE_EXIT,
            "Default exit checklist. Due dates are relative to the last working date.", List.of(
            new TaskDef("Knowledge transfer", "Hand over ongoing work, documents and credentials to the team.",
                    ChecklistConstants.OWNER_MANAGER, -3, false, null, null),
            new TaskDef("Return company assets", "Return laptop, ID card, access cards and any other company property.",
                    ChecklistConstants.OWNER_NEW_HIRE, 0, false, null, null),
            new TaskDef("Revoke system access", "Disable email, VPN and application accounts; collect devices.",
                    ChecklistConstants.OWNER_IT, 0, false, null, null),
            new TaskDef("Full and final settlement", "Compute and process the full and final settlement.",
                    ChecklistConstants.OWNER_HR, 30, false, null, "SALARY"),
            new TaskDef("Issue relieving and experience letters", "Generate the relieving / experience letters from the Letters tab.",
                    ChecklistConstants.OWNER_HR, 1, false, null, "RELIEVING_LETTER")));

    private DefaultChecklistTemplates() {
    }
}
