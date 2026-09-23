execute if score #k timber.data < #rs timber.data run particle minecraft:poof ~ ~ ~ 0.35 0.35 0.35 0.02 5
$execute if score #k timber.data >= #rs timber.data run particle minecraft:poof ~ ~ ~ $(cw) $(cw) $(cw) 0.05 22
$execute if score #k timber.data >= #rs timber.data run particle minecraft:cloud ~ ~ ~ $(cw) $(cw) $(cw) 0.02 5
scoreboard players operation #m timber.data = #k timber.data
scoreboard players operation #m timber.data %= #2 timber.data
execute if score #m timber.data matches 0 run summon marker ~ ~ ~ {Tags:["timber.pt"]}
execute if score #k timber.data = #h2 timber.data run summon marker ~ ~ ~ {Tags:["timber.mid"]}
scoreboard players add #k timber.data 1
execute if score #k timber.data < #h timber.data positioned ^ ^1 ^ run function timber:anim/step_poof with storage timber:anim s
