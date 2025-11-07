package com.star.titleplugin;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TitleTemplate implements Serializable {
    private final String id;           // 템플릿 ID (사용자 지정 또는 UUID)
    private final String name;         // 칭호명 (색코드 제거한 텍스트)
    private final String display;      // MiniMessage 또는 &코드 포함 표시 텍스트
    private final TitleData.Type type; // NORMAL, GRADIENT

    public TitleTemplate(String id, String name, String display, TitleData.Type type) {
        this.id = id;
        this.name = name;
        this.display = display;
        this.type = type;
    }

    public static TitleTemplate fromTitleData(TitleData data) {
        return new TitleTemplate(UUID.randomUUID().toString(), data.getName(), data.getDisplay(), data.getType());
    }

    public static TitleTemplate fromTitleDataWithId(TitleData data, String customId) {
        return new TitleTemplate(customId, data.getName(), data.getDisplay(), data.getType());
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDisplay() { return display; }
    public TitleData.Type getType() { return type; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TitleTemplate other)) return false;
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}