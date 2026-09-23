scoreboard players operation #b timber.data = #tx timber.data
execute if score #b timber.data matches ..-1 run scoreboard players operation #b timber.data *= #-1 timber.data
scoreboard players add #b timber.data 500
scoreboard players operation #cw timber.data > #b timber.data
