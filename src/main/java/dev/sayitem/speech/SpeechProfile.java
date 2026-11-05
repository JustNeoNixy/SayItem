package dev.sayitem.speech;

public enum SpeechProfile {
    CPU("cpu", "CPU Base"), GPU_BASE("gpu_base", "GPU Base"), GPU_SMALL("gpu_small", "GPU Small");
    private final String id, label;
    SpeechProfile(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public boolean gpu() { return this != CPU; }
    public static SpeechProfile parse(String value) {
        for (var profile : values()) if (profile.id.equals(value)) return profile;
        return CPU;
    }
    public SpeechProfile next() { return values()[(ordinal() + 1) % values().length]; }
}
