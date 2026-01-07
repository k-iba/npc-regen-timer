package com.npcregentimer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class NPCRegenState
{
    private int lastRatio;
    private int lastScale;
    private long regenStartMillis; // 0 if no active timer
}