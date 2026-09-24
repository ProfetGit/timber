execute unless score #go tscene matches 1 run return 0
scoreboard players set #go tscene 0
execute as @a[tag=tscene.ready,limit=1] run function tscene:look with storage tscene:cfg cam
