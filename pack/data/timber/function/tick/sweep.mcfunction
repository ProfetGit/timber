# tree displays that lost their controller (1.3.0 left a dropped tree behind): every display stands on its controller (a tree falling onto the ground takes its controller down with it)
tag @e[type=block_display,tag=timber.d] add timber.orphan
execute as @e[type=marker,tag=timber.ctl] at @s run tag @e[type=block_display,tag=timber.orphan,distance=..1.01] remove timber.orphan
kill @e[type=block_display,tag=timber.orphan]
