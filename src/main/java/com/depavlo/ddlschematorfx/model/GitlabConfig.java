package com.depavlo.ddlschematorfx.model;

import java.util.Objects;

// Клас для представлення конфігурації GitLab
public class GitlabConfig {
    private String id; // Унікальний ідентифікатор конфігурації (якщо потрібно зберігати кілька)
    private String url; // URL GitLab
    private String personalAccessToken; // Personal Access Token (має зберігатися зашифрованим)
    private String repository; // Шлях до репозиторію (group/project)
    private String branch; // Гілка для конфігурацій
    private String configFilePath; // Шлях до файлу конфігурації в репозиторії
    private boolean useMergeRequests; // Флаг для використання режиму MR

    // Конструктор
    public GitlabConfig(String id, String url, String personalAccessToken, String repository, String branch, String configFilePath, boolean useMergeRequests) {
        this.id = id;
        this.url = url;
        this.personalAccessToken = personalAccessToken;
        this.repository = repository;
        this.branch = branch;
        this.configFilePath = configFilePath;
        this.useMergeRequests = useMergeRequests;
    }

    // Гетери
    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getPersonalAccessToken() {
        return personalAccessToken;
    }

    public String getRepository() {
        return repository;
    }

    public String getBranch() {
        return branch;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public boolean isUseMergeRequests() {
        return useMergeRequests;
    }

    // Сетери (якщо потрібні)
    // public void setPersonalAccessToken(String personalAccessToken) { this.personalAccessToken = personalAccessToken; }
    // public void setUseMergeRequests(boolean useMergeRequests) { this.useMergeRequests = useMergeRequests; }


    @Override
    public String toString() {
        return "GitlabConfig{" +
               "id='" + id + '\'' +
               ", url='" + url + '\'' +
               ", repository='" + repository + '\'' +
               ", branch='" + branch + '\'' +
               ", configFilePath='" + configFilePath + '\'' +
               ", useMergeRequests=" + useMergeRequests +
               '}';
    }

    // Методи equals та hashCode для порівняння об'єктів GitlabConfig за їх ID
     @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitlabConfig that = (GitlabConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}