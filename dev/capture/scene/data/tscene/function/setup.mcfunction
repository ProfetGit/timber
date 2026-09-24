tag @s add tscene.ready
kill @e[type=item]
$tp @s $(x) $(y) $(z) $(yaw) $(pitch)
gamemode survival @s
item replace entity @s weapon.mainhand with minecraft:iron_axe
scoreboard players set #welcome timber.config 0
scoreboard players set #feedback timber.config 0
scoreboard players set @s timber.off 0
tag @s add timber.welcomed
scoreboard players set #t tscene 0
