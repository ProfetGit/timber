# tip bend for a tip offset of a/1000 blocks: the chain moves the tip about bend * h / 3
$scoreboard players set #a timber.data $(a)
scoreboard players operation #a timber.data *= #172 timber.data
scoreboard players operation #a timber.data /= #10 timber.data
scoreboard players operation #a timber.data /= @s timber.h
scoreboard players operation @s timber.bn = #a timber.data
scoreboard players set @s timber.bv 0
