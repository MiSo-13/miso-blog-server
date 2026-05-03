package com.miso.blog.localrepo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "blog.local-repositories")
public class LocalRepositoryDefaultsProperties {
    private List<DefaultRepository> defaults = new ArrayList<>();

    @Getter
    @Setter
    public static class DefaultRepository {
        private String name;
        private String localPath;
        private String defaultBranch = "main";
        private String description;
        private Boolean active = true;
    }
}
