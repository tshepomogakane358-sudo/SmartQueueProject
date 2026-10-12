package com.smartqueue.model;

public class Staff {

    private int     staffId;
    private String  name;
    private String  username;
    private String  passwordHash;
    private boolean active;

    public Staff() {}

    public int     getStaffId()      { return staffId; }
    public String  getName()         { return name; }
    public String  getUsername()     { return username; }
    public String  getPasswordHash() { return passwordHash; }
    public boolean isActive()        { return active; }

    public void setStaffId(int staffId)             { this.staffId      = staffId; }
    public void setName(String name)                { this.name         = name; }
    public void setUsername(String username)        { this.username     = username; }
    public void setPasswordHash(String hash)        { this.passwordHash = hash; }
    public void setActive(boolean active)           { this.active       = active; }

    @Override
    public String toString() {
        return "Staff [ID=" + staffId + " | " + name + " | " + username
                + " | active=" + active + "]";
    }
}