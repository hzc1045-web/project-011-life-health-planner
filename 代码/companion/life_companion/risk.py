from __future__ import annotations

import re

from .models import RiskLevel

URGENT_PATTERNS = (
    r"胸痛.*(呼吸|出汗|晕)",
    r"呼吸(非常|严重)?困难",
    r"无法呼吸",
    r"一侧.*(无力|麻木)",
    r"言语不清",
    r"大量出血",
    r"失去意识",
    r"自杀|轻生|伤害自己",
)

CAUTION_PATTERNS = (
    r"持续发烧",
    r"反复头晕",
    r"药物.*(过敏|皮疹)",
    r"血压.*(很高|很低)",
)


def detect_risk(text: str) -> RiskLevel:
    normalized = " ".join(text.strip().split())
    if any(re.search(pattern, normalized, flags=re.IGNORECASE) for pattern in URGENT_PATTERNS):
        return RiskLevel.URGENT
    if any(re.search(pattern, normalized, flags=re.IGNORECASE) for pattern in CAUTION_PATTERNS):
        return RiskLevel.CAUTION
    return RiskLevel.NORMAL


def urgent_message(emergency_number: str) -> str:
    contact = emergency_number.strip() or "当地急救电话"
    return (
        "你描述的情况可能需要立即处理。"
        f"请停止使用本计划工具并联系 {contact}，或由身边的人陪同前往急诊。"
    )
