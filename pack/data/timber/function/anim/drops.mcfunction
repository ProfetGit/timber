execute unless data storage timber:anim q[0] run return 0
execute unless entity @e[type=marker,tag=timber.pt,limit=1] run summon marker ~ ~0.5 ~ {Tags:["timber.pt"]}
tag @a remove timber.owner
execute if score #drops timber.config matches 1 run function timber:anim/owner with storage timber:anim s
function timber:anim/drop_one
tag @a remove timber.owner
