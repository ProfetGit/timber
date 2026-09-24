execute if score #lg timber.data matches 0 run data modify storage timber:op ldrops append from entity @s Item
execute if score #lg timber.data matches 1 run data modify storage timber:op drops append value {}
execute if score #lg timber.data matches 1 run data modify storage timber:op drops[-1].Item set from entity @s Item
execute if score #lg timber.data matches 1 store result storage timber:op drops[-1].y int 1 run scoreboard players get #cy timber.data
kill @s
