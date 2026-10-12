package com.smartqueue.service;

import com.smartqueue.dao.StaffDAO;
import com.smartqueue.model.Staff;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;


public class StaffService {

    private final StaffDAO staffDAO = new StaffDAO();

    // Lowercase letters, digits, dot, underscore, hyphen. No "@", so a staff
    // username can never be mistaken for a patient's email address.
    private static final String USERNAME_PATTERN = "[a-z0-9._-]{3,30}";
    private static final int    MIN_PASSWORD_LENGTH = 6;

    // "admin" is handled by the separate administrator login.
    private static final String RESERVED_USERNAME = "admin";


    public StaffResult login(String username, String password) {
        if (username == null || password == null
                || username.isBlank() || password.isEmpty()) {
            return StaffResult.fail("Invalid username or password.");
        }

        Staff staff = staffDAO.getStaffByUsername(normalise(username));
        if (staff == null || !passwordMatches(password, staff.getPasswordHash())) {
            return StaffResult.fail("Invalid username or password.");
        }

        // Only reveal that an account is disabled once the password was correct.
        if (!staff.isActive()) {
            return StaffResult.fail("This account has been disabled. Please contact an administrator.");
        }

        return StaffResult.success("Login successful.", staff);
    }


    public List<Staff> getAllStaff() {
        return staffDAO.getAllStaff();
    }


    public StaffResult createStaff(String name, String username, String password) {
        String error = validateDetails(name, username);
        if (error != null) return StaffResult.fail(error);

        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return StaffResult.fail("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        String cleanUsername = normalise(username);
        if (staffDAO.getStaffByUsername(cleanUsername) != null) {
            return StaffResult.fail("That username is already taken.");
        }

        Staff staff = new Staff();
        staff.setName(name.trim());
        staff.setUsername(cleanUsername);
        staff.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));

        if (!staffDAO.createStaff(staff)) {
            return StaffResult.fail("Could not create the staff account.");
        }
        return StaffResult.success("Staff account created.", null);
    }



    public StaffResult updateStaff(int staffId, String name, String username, String newPassword) {
        Staff existing = staffDAO.getStaffById(staffId);
        if (existing == null) {
            return StaffResult.fail("Staff account not found.");
        }

        String error = validateDetails(name, username);
        if (error != null) return StaffResult.fail(error);

        boolean changingPassword = newPassword != null && !newPassword.isEmpty();
        if (changingPassword && newPassword.length() < MIN_PASSWORD_LENGTH) {
            return StaffResult.fail("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        String cleanUsername = normalise(username);
        Staff sameUsername = staffDAO.getStaffByUsername(cleanUsername);
        if (sameUsername != null && sameUsername.getStaffId() != staffId) {
            return StaffResult.fail("That username is already taken.");
        }

        if (!staffDAO.updateStaff(staffId, name.trim(), cleanUsername)) {
            return StaffResult.fail("Could not update the staff account.");
        }

        if (changingPassword) {
            String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            if (!staffDAO.updatePassword(staffId, hash)) {
                return StaffResult.fail("Details saved, but the password could not be changed.");
            }
        }

        return StaffResult.success("Staff account updated.", null);
    }


    public StaffResult setActive(int staffId, boolean active) {
        if (staffDAO.getStaffById(staffId) == null) {
            return StaffResult.fail("Staff account not found.");
        }
        if (!staffDAO.setActive(staffId, active)) {
            return StaffResult.fail("Could not change the account status.");
        }
        return StaffResult.success(active ? "Account enabled." : "Account disabled.", null);
    }


    public StaffResult deleteStaff(int staffId) {
        if (staffDAO.getStaffById(staffId) == null) {
            return StaffResult.fail("Staff account not found.");
        }
        if (!staffDAO.deleteStaff(staffId)) {
            return StaffResult.fail("Could not delete the staff account.");
        }
        return StaffResult.success("Staff account deleted.", null);
    }


    // ── helpers ───────────────────────────────────────────

    private String normalise(String username) {
        return username.trim().toLowerCase();
    }

    private String validateDetails(String name, String username) {
        if (name == null || name.isBlank()) {
            return "Full name is required.";
        }
        if (username == null || username.isBlank()) {
            return "Username is required.";
        }
        String clean = normalise(username);
        if (!clean.matches(USERNAME_PATTERN)) {
            return "Username must be 3-30 characters using letters, numbers, dots, underscores or hyphens (no spaces or @).";
        }
        if (clean.equals(RESERVED_USERNAME)) {
            return "That username is reserved.";
        }
        return null;
    }

    private boolean passwordMatches(String password, String hash) {
        try {
            return hash != null && BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            return false; // stored hash is not a valid bcrypt hash
        }
    }


    public static class StaffResult {
        private final boolean success;
        private final String  message;
        private final Staff   staff;

        private StaffResult(boolean success, String message, Staff staff) {
            this.success = success;
            this.message = message;
            this.staff   = staff;
        }

        public static StaffResult success(String message, Staff staff) {
            return new StaffResult(true, message, staff);
        }

        public static StaffResult fail(String message) {
            return new StaffResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String  getMessage() { return message; }
        public Staff   getStaff()   { return staff; }
    }
}