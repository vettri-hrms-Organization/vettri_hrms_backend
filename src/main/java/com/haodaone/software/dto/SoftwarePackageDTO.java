package com.haodaone.software.dto;

import com.haodaone.software.entity.SoftwarePackage;
import com.haodaone.software.entity.SoftwarePlatform;

public class SoftwarePackageDTO {
    private Long id;
    private String name;
    private String publisher;
    private String description;
    private SoftwarePlatform platform;
    private boolean active;

    public static class CreateRequest {
        private String name;
        private String publisher;
        private String description;
        private SoftwarePlatform platform;
        private boolean active = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPublisher() { return publisher; }
        public void setPublisher(String publisher) { this.publisher = publisher; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public SoftwarePlatform getPlatform() { return platform; }
        public void setPlatform(SoftwarePlatform platform) { this.platform = platform; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public static SoftwarePackageDTO from(SoftwarePackage entity) {
        if (entity == null) return null;
        SoftwarePackageDTO dto = new SoftwarePackageDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setPublisher(entity.getPublisher());
        dto.setDescription(entity.getDescription());
        dto.setPlatform(entity.getPlatform());
        dto.setActive(entity.isActive());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public SoftwarePlatform getPlatform() { return platform; }
    public void setPlatform(SoftwarePlatform platform) { this.platform = platform; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
