package models;

public class FileInfo {

    private String originalName;
    private String folder;
    private Integer serial;
    private Integer year;
    private Integer periodNumber;
    private String monthName;
    private boolean isMissing;
    private boolean isHoldings;
    private String finalName;

    public String getOriginalName() {
        return originalName;
    }
    public String getFolder() {
        return folder;
    }
    public Integer getSerial() {
        return serial;
    }
    public Integer getYear() {
        return year;
    }
    public Integer getPeriodNumber() {
        return periodNumber;
    }
    public String getMonthName() {
        return monthName;
    }
    public boolean isMissing() {
        return isMissing;
    }
    public boolean isHoldings() {
        return isHoldings;
    }
    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }
    public void setFolder(String folder) {
        this.folder = folder;
    }
    public void setSerial(Integer serial) {
        this.serial = serial;
    }
    public void setYear(Integer year) {
        this.year = year;
    }
    public void setPeriodNumber(Integer periodNumber) {
        this.periodNumber = periodNumber;
    }
    public void setMonthName(String monthName) {
        this.monthName = monthName;
    }
    public void setMissing(boolean isMissing) {
        this.isMissing = isMissing;
    }
    public void setHoldings(boolean isHoldings) {
        this.isHoldings = isHoldings;
    }
    public String getFinalName() {
        return finalName;
    }
    public void setFinalName(String finalName) {
        this.finalName = finalName;
    }   
}