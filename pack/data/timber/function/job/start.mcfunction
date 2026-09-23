scoreboard players set #stage timber.data 1
tag @s remove timber.job
tag @s add timber.cur
tag @s add timber.m
tag @s add timber.origin
scoreboard players operation #jobcur timber.data = @s timber.job
data modify storage timber:op t set from entity @s data.t
data modify storage timber:op k set from entity @s data.k
tag @a remove timber.cutter
execute as @a if score @s timber.job = #jobcur timber.data run tag @s add timber.cutter
execute unless entity @a[tag=timber.cutter,limit=1] run return run function timber:job/abort
scoreboard players operation #rx0 timber.data = @s timber.x
scoreboard players operation #rx0 timber.data -= #radius timber.config
scoreboard players operation #rx1 timber.data = @s timber.x
scoreboard players operation #rx1 timber.data += #radius timber.config
scoreboard players operation #rz0 timber.data = @s timber.z
scoreboard players operation #rz0 timber.data -= #radius timber.config
scoreboard players operation #rz1 timber.data = @s timber.z
scoreboard players operation #rz1 timber.data += #radius timber.config
scoreboard players set #logs timber.data 0
scoreboard players set #lab timber.data 0
execute unless block ~ ~-1 ~ #timber:not_ground run tag @s add timber.g
function timber:flood/neighbors with storage timber:op t
