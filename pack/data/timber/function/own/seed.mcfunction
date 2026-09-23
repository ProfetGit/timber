execute if entity @s[tag=timber.claimed] run return 0
scoreboard players add #lab timber.data 1
scoreboard players operation #l timber.data = #lab timber.data
function timber:own/claim
function timber:own/seed_grow
