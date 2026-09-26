# ring #k: its origin on the (bent) chain, and its base height for the displays' offsets
execute if score #k timber.data matches 0 run scoreboard players set #oy timber.data 0
execute if score #k timber.data matches 0 run scoreboard players set #oz timber.data 0
execute if score #k timber.data matches ..-1 run function timber:anim/xf_rigid
scoreboard players operation #kk timber.data = #k timber.data
scoreboard players operation #kk timber.data *= #gy timber.data
scoreboard players operation #oy4 timber.data = #oy timber.data
scoreboard players operation #oy4 timber.data *= #10000 timber.data
scoreboard players operation #oz4 timber.data = #oz timber.data
scoreboard players operation #oz4 timber.data *= #10000 timber.data
