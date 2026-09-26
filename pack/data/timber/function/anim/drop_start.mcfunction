function timber:anim/phase {ph:5}
tag @s add timber.dropped
execute store result score @s timber.y0 run data get entity @s Pos[1] 1000
scoreboard players operation @s timber.dur = @s timber.dr
scoreboard players add @s timber.dur 2
execute if score @s timber.dur matches 11.. run scoreboard players set @s timber.dur 10
