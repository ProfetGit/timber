# the whole tree squashes (a > 0) or stretches (a < 0) by a/1000 blocks at its top, and widens by half as much
$scoreboard players set #a timber.data $(a)
scoreboard players operation #a timber.data /= @s timber.h
scoreboard players set @s timber.gy 1000
scoreboard players operation @s timber.gy -= #a timber.data
scoreboard players operation #a timber.data /= #2 timber.data
scoreboard players set @s timber.gx 1000
scoreboard players operation @s timber.gx += #a timber.data
scoreboard players operation @s timber.gz = @s timber.gx
scoreboard players operation @s timber.lx = @s timber.gx
scoreboard players operation @s timber.lz = @s timber.gx
tag @s add timber.xf
