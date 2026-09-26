scoreboard players operation #fr timber.data = @s timber.hit
scoreboard players operation #fr timber.data > #9000 timber.data
scoreboard players add #fr timber.data 1000
scoreboard players operation #f timber.data *= #fr timber.data
scoreboard players operation #f timber.data /= #10000 timber.data
scoreboard players remove #f timber.data 1000
