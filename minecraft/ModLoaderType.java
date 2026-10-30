package io.github.ufml.launcher.minecraft;

public enum ModLoaderType {
    VANILLA("原版", "只安装 Mojang 官方客户端"),
    FABRIC("Fabric", "安装 Fabric Loader，可加载 Fabric 模组"),
    FABRIC_API("Fabric + API", "安装 Fabric Loader 与对应版本的 Fabric API"),
    FORGE("Forge", "调用官方 Forge 安装器并生成本地实例");

    private final String label;
    private final String description;

    ModLoaderType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }

    @Override
    public String toString() { return label; }
}
