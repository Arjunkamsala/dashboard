package com.fiberify.dashboard.model;

import java.util.ArrayList;
import java.util.List;

public class BlockNode {
    private String name;
    private String blockCode;
    private String ip;
    private String district;
    private String gpBlock;
    private String status; // "UP" or "UNREACHABLE"
    private String alarm = "--";
    private String stateChange = "--";
    private int gpCount;
    private List<GpEntry> gps = new ArrayList<>();

    public BlockNode() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getGpBlock() { return gpBlock; }
    public void setGpBlock(String gpBlock) { this.gpBlock = gpBlock; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAlarm() { return alarm; }
    public void setAlarm(String alarm) { this.alarm = alarm; }

    public String getStateChange() { return stateChange; }
    public void setStateChange(String stateChange) { this.stateChange = stateChange; }

    public int getGpCount() { return gpCount; }
    public void setGpCount(int gpCount) { this.gpCount = gpCount; }

    public List<GpEntry> getGps() { return gps; }
    public void setGps(List<GpEntry> gps) { this.gps = gps; }

    public void addGp(GpEntry gp) {
        this.gps.add(gp);
        this.gpCount = this.gps.size();
    }
}
