$setblock $(cx) $(cy) $(cz) air destroy
$rotate @s $(fyaw) 20
$execute positioned $(fx) $(fy) $(fz) rotated $(fyaw) 20 run function timber:mined {t:"$(t)"}
scoreboard players set #go tscene 1
