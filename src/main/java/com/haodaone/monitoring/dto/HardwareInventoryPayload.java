package com.haodaone.monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class HardwareInventoryPayload {

    @Size(max = 150)
    private String computerName;
    @Size(max = 150)
    private String manufacturer;
    @Size(max = 150)
    private String model;
    @Size(max = 150)
    private String serialNumber;
    @Size(max = 255)
    private String processorName;
    @Size(max = 150)
    private String processorManufacturer;
    @Positive
    private Integer physicalCoreCount;
    @Positive
    private Integer logicalProcessorCount;
    @Positive
    private Long installedMemoryBytes;
    @Valid
    @Size(max = 32)
    private List<@NotNull @Valid PhysicalDisk> physicalDisks = new ArrayList<>();
    @Size(max = 255)
    private String windowsEdition;
    @Size(max = 100)
    private String windowsVersion;
    @Size(max = 50)
    private String windowsBuild;
    @Size(max = 50)
    private String architecture;
    @Size(max = 150)
    private String biosManufacturer;
    @Size(max = 255)
    private String biosVersion;
    private OffsetDateTime biosReleaseDate;
    @Size(max = 100)
    private String systemUuid;
    @NotBlank
    @Size(max = 50)
    private String agentVersion;
    @NotNull
    private OffsetDateTime collectedAt;

    public String getComputerName() { return computerName; }
    public void setComputerName(String computerName) { this.computerName = computerName; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getProcessorName() { return processorName; }
    public void setProcessorName(String processorName) { this.processorName = processorName; }
    public String getProcessorManufacturer() { return processorManufacturer; }
    public void setProcessorManufacturer(String processorManufacturer) { this.processorManufacturer = processorManufacturer; }
    public Integer getPhysicalCoreCount() { return physicalCoreCount; }
    public void setPhysicalCoreCount(Integer physicalCoreCount) { this.physicalCoreCount = physicalCoreCount; }
    public Integer getLogicalProcessorCount() { return logicalProcessorCount; }
    public void setLogicalProcessorCount(Integer logicalProcessorCount) { this.logicalProcessorCount = logicalProcessorCount; }
    public Long getInstalledMemoryBytes() { return installedMemoryBytes; }
    public void setInstalledMemoryBytes(Long installedMemoryBytes) { this.installedMemoryBytes = installedMemoryBytes; }
    public List<PhysicalDisk> getPhysicalDisks() { return physicalDisks; }
    public void setPhysicalDisks(List<PhysicalDisk> physicalDisks) { this.physicalDisks = physicalDisks; }
    public String getWindowsEdition() { return windowsEdition; }
    public void setWindowsEdition(String windowsEdition) { this.windowsEdition = windowsEdition; }
    public String getWindowsVersion() { return windowsVersion; }
    public void setWindowsVersion(String windowsVersion) { this.windowsVersion = windowsVersion; }
    public String getWindowsBuild() { return windowsBuild; }
    public void setWindowsBuild(String windowsBuild) { this.windowsBuild = windowsBuild; }
    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }
    public String getBiosManufacturer() { return biosManufacturer; }
    public void setBiosManufacturer(String biosManufacturer) { this.biosManufacturer = biosManufacturer; }
    public String getBiosVersion() { return biosVersion; }
    public void setBiosVersion(String biosVersion) { this.biosVersion = biosVersion; }
    public OffsetDateTime getBiosReleaseDate() { return biosReleaseDate; }
    public void setBiosReleaseDate(OffsetDateTime biosReleaseDate) { this.biosReleaseDate = biosReleaseDate; }
    public String getSystemUuid() { return systemUuid; }
    public void setSystemUuid(String systemUuid) { this.systemUuid = systemUuid; }
    public String getAgentVersion() { return agentVersion; }
    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }
    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(OffsetDateTime collectedAt) { this.collectedAt = collectedAt; }

    public static class PhysicalDisk {
        @Size(max = 255)
        private String model;
        @Positive
        private Long capacityBytes;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Long getCapacityBytes() { return capacityBytes; }
        public void setCapacityBytes(Long capacityBytes) { this.capacityBytes = capacityBytes; }
    }
}
