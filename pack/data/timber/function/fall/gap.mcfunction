# open cells straight under the cut (smallest over the trunk columns): the tree drops this far before it tips. No ground
# within 16 blocks counts as no gap
scoreboard players set #gap timber.data 99
execute at @e[type=marker,tag=timber.cur,limit=1] as @e[type=marker,tag=timber.ours,distance=..1.5] if score @s timber.y = #oy timber.data at @s positioned ~ ~-1 ~ run function timber:fall/gap_col
execute if score #gap timber.data matches 17.. run scoreboard players set #gap timber.data 0
