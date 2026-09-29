package com.cardbattle.engine.effect;

/** 효과 안에 다른 효과를 품고 있어 레지스트리가 필요한 핸들러 (CHANCE, RANDOM_CHOICE, APPLY_CURSE 등) */
public interface NestedEffects {

    void bind(EffectRegistry registry);
}
