package com.star.titleplugin;

import java.io.Serializable;
import java.time.LocalDateTime;

public class TitleData implements Serializable {
    public enum Type { NORMAL, GRADIENT }

    private final String name;      // 칭호명 (색코드/미니메시지 제거한 실제 텍스트)
    private final String display;   // 실제 GUI/채팅에 쓸 MiniMessage 텍스트
    private final Type type;

    // 카탈로그(템플릿)와 연결하기 위한 선택적 ID (null일 수 있음, 레거시 호환)
    private final String templateId;

    private final LocalDateTime acquiredAt; // 획득 시간 필드 추가

    // 기존 생성자와 호환 가능하도록 수정 (획득 시간 자동 추가)
    public TitleData(String name, String display, Type type) {
        this(name, display, type, null, LocalDateTime.now());
    }

    public TitleData(String name, String display, Type type, String templateId) {
        this(name, display, type, templateId, LocalDateTime.now());
    }

    // 신규 생성자 (획득 시간 직접 지정)
    public TitleData(String name, String display, Type type, String templateId, LocalDateTime acquiredAt) {
        this.name = name;
        this.display = display;
        this.type = type;
        this.templateId = templateId;
        this.acquiredAt = acquiredAt;
    }

    // Getter
    public String getName() { return name; }
    public String getDisplay() { return display; }
    public Type getType() { return type; }
    public String getTemplateId() { return templateId; }
    public LocalDateTime getAcquiredAt() { return acquiredAt; }

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