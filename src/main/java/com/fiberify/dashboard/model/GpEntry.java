package com.fiberify.dashboard.model;

public class GpEntry {
    private String loc;
    private String code;
    private String status = "UP"; // "UP" or "DOWN"

    public GpEntry() {}

    public GpEntry(String loc, String code) {
        this.loc = loc;
        this.code = code;
    }

    public GpEntry(String loc, String code, String status) {
        this.loc = loc;
        this.code = code;
        this.status = status;
    }

    public String getLoc() { return loc; }
    public void setLoc(String loc) { this.loc = loc; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

}
