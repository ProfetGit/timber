execute as @e[type=marker,tag=timber.m] at @s run function timber:job/restore with storage timber:op t
kill @e[type=marker,tag=timber.m]
kill @e[type=marker,tag=timber.cur]
tag @a remove timber.cutter
scoreboard players set #stage timber.data 0
