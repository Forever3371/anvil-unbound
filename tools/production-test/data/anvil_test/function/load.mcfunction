# 生产环境（intermediary 命名）共存验证：Anvil Unbound + Blast Enchant
# 两个模组都放宽了附魔等级的 255 硬上限，直接各自注入会冲突；
# Anvil Unbound 用 MixinConfigPlugin 检测到 blast_enchant 时会让位（跳过自己的三个上限 Mixin）。
# 数据包解析阶段命令树还没建好，函数里不能写 stop。
say [AnvilTest] coexistence test START
scoreboard objectives add anviltest dummy
execute store result score AnvilSelfTest anviltest run anvilunbound selftest
execute if score AnvilSelfTest anviltest matches 1.. run say [AnvilTest] PASS_ANVIL_SELFTEST
execute if score AnvilSelfTest anviltest matches 0 run say [AnvilTest] FAIL_ANVIL_SELFTEST
execute store result score BlastSelfTest anviltest run blastenchant selftest
execute if score BlastSelfTest anviltest matches 1.. run say [AnvilTest] PASS_BLAST_SELFTEST
execute if score BlastSelfTest anviltest matches 0 run say [AnvilTest] FAIL_BLAST_SELFTEST
say [AnvilTest] coexistence test DONE
