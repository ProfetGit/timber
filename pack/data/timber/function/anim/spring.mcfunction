# the trunk bends like a rod: its tip lags behind when the tree speeds up and swings through when it slows down
# (a damped spring driven by the trunk's angular acceleration, centi-degrees per tick)
scoreboard players operation #a timber.data = @s timber.bn
scoreboard players operation #a timber.data *= #flex_k timber.data
scoreboard players operation #a timber.data /= #100 timber.data
scoreboard players operation @s timber.bv -= #a timber.data
scoreboard players operation #a timber.data = @s timber.bv
scoreboard players operation #a timber.data *= #flex_c timber.data
scoreboard players operation #a timber.data /= #100 timber.data
scoreboard players operation @s timber.bv -= #a timber.data
scoreboard players operation #a timber.data = #acc timber.data
scoreboard players operation #a timber.data *= #flex_m timber.data
scoreboard players operation #a timber.data /= #100 timber.data
scoreboard players operation @s timber.bv -= #a timber.data
scoreboard players operation @s timber.bn += @s timber.bv
execute if score @s timber.bn matches 701.. run scoreboard players set @s timber.bn 700
execute if score @s timber.bn matches ..-701 run scoreboard players set @s timber.bn -700
