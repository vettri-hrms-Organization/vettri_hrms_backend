package com.haodaone.leave.service;

import com.haodaone.company.entity.Company;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveTypeRepository;

/** The starter leave types every new company gets; admins can edit them under Settings > Leave Types. */
public final class DefaultLeaveTypes {

    private DefaultLeaveTypes() {
    }

    public static void seedFor(Company company, LeaveTypeRepository leaveTypes) {
        if (!leaveTypes.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(company.getId()).isEmpty()) {
            return;
        }
        save(company, leaveTypes, "Casual Leave", "CL", 12, false);
        save(company, leaveTypes, "Sick Leave", "SL", 10, false);
        save(company, leaveTypes, "Earned Leave", "EL", 15, true);
    }

    private static void save(Company company, LeaveTypeRepository leaveTypes, String name, String code,
                             double daysPerYear, boolean carryForward) {
        LeaveType type = new LeaveType();
        type.setCompany(company);
        type.setName(name);
        type.setCode(code);
        type.setDefaultDaysPerYear(daysPerYear);
        type.setCarryForward(carryForward);
        leaveTypes.save(type);
    }
}
