package com.star.titleplugin;

import java.io.Serializable;

public class TitleData implements Serializable {
    public enum Type { NORMAL, GRADIENT }

    private final String name;      // 칭호명 (색코드/미니메시지 제거한 실제 텍스트)
    private final String display;   // 실제 GUI/채팅에 쓸 MiniMessage 텍스트
    private final Type type;

    // 카탈로그(템플릿)와 연결하기 위한 선택적 ID (null일 수 있음, 레거시 호환)
    private final String templateId;

    public TitleData(String name, String display, Type type) {
        this(name, display, type, null);
    }

    public TitleData(String name, String display, Type type, String templateId) {
        this.name = name;
        this.display = display;
        this.type = type;
        this.templateId = templateId;
    }

    public String getName() { return name; }
    public String getDisplay() { return display; }
    public Type getType() { return type; }
    public String getTemplateId() { return templateId; }

    // 동등성: templateId가 양쪽 모두 있으면 templateId 기준, 없으면 기존 name+type 기준
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TitleData other)) return false;
        if (this.templateId != null && other.templateId != null) {
            return this.templateId.equals(other.templateId);
        }
        return name.equals(other.name) && type == other.type;
    }

    @Override
    public int hashCode() {
        if (this.templateId != null) return this.templateId.hashCode();
        return name.hashCode() * 31 + type.hashCode();
    }
}