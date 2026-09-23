data modify storage timber:anim s set from entity @s data
scoreboard players operation #pe timber.data = @s timber.p
scoreboard players operation #pe timber.data += @s timber.e
execute store result storage timber:anim s.p double 0.01 run scoreboard players get #pe timber.data
scoreboard players operation #h timber.data = @s timber.h
execute store result score #rs timber.data run data get storage timber:anim s.rs
scoreboard players set #k timber.data 0
