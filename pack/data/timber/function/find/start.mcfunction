scoreboard players set #found timber.data 0
$execute anchored eyes positioned ^ ^ ^ as @e[type=item,distance=..8,nbt={Age:0s,Item:{id:"$(log)"}},sort=nearest] at @s align xyz if block ~ ~ ~ #timber:hollow positioned ~0.5 ~0.5 ~0.5 run function timber:find/candidate
execute if score #found timber.data matches 1.. run return 1
scoreboard players set #ray timber.data 0
execute anchored eyes positioned ^ ^ ^ run function timber:find/ray
