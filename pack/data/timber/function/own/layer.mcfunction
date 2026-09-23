tag @e[type=marker,tag=timber.next] add timber.front
tag @e[type=marker,tag=timber.next] remove timber.next
execute unless entity @e[type=marker,tag=timber.front,limit=1] run return 0
execute as @e[type=marker,tag=timber.front] at @s run function timber:own/spread
tag @e[type=marker,tag=timber.front] remove timber.front
function timber:own/layer
