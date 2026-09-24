scoreboard players operation #m timber.data = #k timber.data
scoreboard players operation #m timber.data %= #2 timber.data
$execute if score #k timber.data >= #rs timber.data positioned ~ ~0.5 ~ run particle minecraft:block{block_state:"$(leaf)"} ~ ~ ~ $(cw) $(cw) $(cw) 0.15 36
$execute if score #k timber.data >= #rs timber.data positioned ~ ~0.5 ~ run particle minecraft:small_gust ~ ~ ~ $(cw) $(cw) $(cw) 0 3
execute if score #k timber.data >= #rs timber.data if score #m timber.data matches 0 run summon marker ~ ~0.5 ~ {Tags:["timber.pt"]}
$execute if score #k timber.data < #rs timber.data if score #m timber.data matches 0 run particle minecraft:block{block_state:"$(log)"} ~ ~ ~ 0.4 0.3 0.4 0 5
scoreboard players add #k timber.data 1
execute if score #k timber.data < #h timber.data positioned ^ ^1 ^ run function timber:anim/step_impact with storage timber:anim s
