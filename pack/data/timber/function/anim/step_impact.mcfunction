$execute if score #k timber.data >= #rs timber.data run particle minecraft:block{block_state:"$(leaf)"} ~ ~ ~ $(cw) 0.4 $(cw) 0 14
$execute if score #k timber.data < #rs timber.data run particle minecraft:block{block_state:"$(log)"} ~ ~ ~ 0.4 0.3 0.4 0 5
scoreboard players add #k timber.data 2
execute if score #k timber.data < #h timber.data positioned ^ ^2 ^ run function timber:anim/step_impact with storage timber:anim s
