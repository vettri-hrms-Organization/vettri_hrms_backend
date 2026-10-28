package com.haodaone.letters.service;

import com.haodaone.letters.entity.LetterType;

import java.util.List;

/** Built-in templates seeded for each company the first time letters are used. */
public final class DefaultLetterTemplates {

    public record Definition(String letterType, String name, String title, String body) {}

    public static final List<Definition> ALL = List.of(
            new Definition(LetterType.OFFER, "Offer letter", "Offer of Employment", """
                    Date: {{today}}

                    Dear {{candidate.fullName}},

                    We are pleased to offer you the position of {{candidate.position}} at {{company.name}}. We were impressed with your background and believe you will be a valuable addition to our team.

                    Your annual cost to company (CTC) will be {{candidate.offerAmount}}. Your expected date of joining is {{candidate.joiningDate}}.

                    This offer is subject to verification of your documents and references. Detailed terms of employment will be shared in your appointment letter on joining.

                    Please sign and return a copy of this letter as a token of your acceptance.

                    We look forward to welcoming you to {{company.name}}."""),
            new Definition(LetterType.APPOINTMENT, "Appointment letter", "Letter of Appointment", """
                    Date: {{today}}

                    Dear {{employee.fullName}},

                    With reference to your application and subsequent discussions, we are pleased to appoint you as {{employee.designation}} in the {{employee.department}} department of {{company.name}} with effect from {{employee.dateOfJoining}}. Your employee code is {{employee.code}}.

                    Your annual cost to company will be {{salary.annualCtc}} (monthly gross {{salary.monthlyGross}}), subject to applicable statutory deductions.

                    You will be on probation for a period of six months from your date of joining. Your services will be confirmed in writing on satisfactory completion of the probation period.

                    You are required to abide by the policies, rules and regulations of the company as amended from time to time.

                    Please sign and return the duplicate copy of this letter as a token of your acceptance."""),
            new Definition(LetterType.CONFIRMATION, "Confirmation letter", "Confirmation of Employment", """
                    Date: {{today}}

                    Dear {{employee.fullName}},

                    We are pleased to inform you that, on successful completion of your probation period, your services as {{employee.designation}} ({{employee.code}}) are hereby confirmed.

                    All other terms and conditions of your appointment remain unchanged.

                    We appreciate your contribution and wish you continued success with {{company.name}}."""),
            new Definition(LetterType.SALARY_REVISION, "Salary revision letter", "Salary Revision", """
                    Date: {{today}}

                    Dear {{employee.fullName}},

                    In recognition of your performance and contribution, we are pleased to revise your compensation. With effect from {{custom.effectiveDate}}, your annual cost to company will be {{salary.annualCtc}} (monthly gross {{salary.monthlyGross}}).

                    All other terms and conditions of your employment remain unchanged.

                    We thank you for your efforts and look forward to your continued contribution to {{company.name}}."""),
            new Definition(LetterType.EXPERIENCE, "Experience letter", "Experience Certificate", """
                    Date: {{today}}

                    TO WHOMSOEVER IT MAY CONCERN

                    This is to certify that {{employee.fullName}} (Employee code {{employee.code}}) was employed with {{company.name}} from {{employee.dateOfJoining}} to {{employee.lastWorkingDate}}. At the time of leaving, the designation held was {{employee.designation}} in the {{employee.department}} department.

                    During the tenure with us, we found the employee to be sincere and hardworking.

                    We wish {{employee.firstName}} all the best in future endeavours."""),
            new Definition(LetterType.RELIEVING, "Relieving letter", "Relieving Letter", """
                    Date: {{today}}

                    Dear {{employee.fullName}},

                    This is with reference to your resignation. We hereby confirm that you have been relieved from the services of {{company.name}} at the close of working hours on {{employee.lastWorkingDate}}.

                    You worked with us as {{employee.designation}} (Employee code {{employee.code}}) from {{employee.dateOfJoining}} to {{employee.lastWorkingDate}}. Your full and final settlement will be processed as per company policy.

                    We thank you for your contribution and wish you success in your future endeavours."""),
            new Definition(LetterType.EMPLOYMENT_VERIFICATION, "Address proof / Employment verification", "Employment Verification Letter", """
                    Date: {{today}}

                    TO WHOMSOEVER IT MAY CONCERN

                    This is to certify that {{employee.fullName}} (Employee code {{employee.code}}) is employed with {{company.name}} as {{employee.designation}} since {{employee.dateOfJoining}}.

                    As per our records, the residential address of the employee is:
                    {{employee.address}}

                    This letter is issued at the request of the employee for the purpose of {{custom.purpose}}.""")
    );

    private DefaultLetterTemplates() {
    }
}
