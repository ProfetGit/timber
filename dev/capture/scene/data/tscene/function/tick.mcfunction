execute as @a[tag=!tscene.ready,limit=1] at @s run function tscene:setup with storage tscene:cfg cam
execute if score #t tscene matches 0.. run scoreboard players add #t tscene 1
execute if score #t tscene matches 50 as @a[tag=tscene.ready,limit=1] run function tscene:trigger with storage tscene:cfg
