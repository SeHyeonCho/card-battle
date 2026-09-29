package com.cardbattle.engine.pack;

import java.util.List;

/** 카드팩 JSON이 잘못됐을 때. 오류 위치가 담긴 메시지 목록을 가진다 (PRD 8.6). */
public class PackFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> errors;

    public PackFormatException(List<String> errors) {
        super("카드팩 오류 " + errors.size() + "건:\n - " + String.join("\n - ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
