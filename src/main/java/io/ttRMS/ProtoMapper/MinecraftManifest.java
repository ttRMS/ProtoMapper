package io.ttrms.protomapper;

import lombok.Data;

import java.util.List;

@Data
public class MinecraftManifest {
    public List<MinecraftVersionInfo> versions;

    @Data
    public static class latest {
        public String release;
        public String snapshot;
    }
}
