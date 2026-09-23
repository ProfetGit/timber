scoreboard players operation #yi timber.data = #yaw timber.data
scoreboard players operation #yi timber.data /= #2250 timber.data
function timber:fall/sincos
scoreboard players operation #px timber.data = #hw timber.data
scoreboard players operation #px timber.data *= #sin timber.data
scoreboard players operation #px timber.data /= #10000 timber.data
scoreboard players operation #px timber.data *= #-1 timber.data
scoreboard players operation #px timber.data += #fcx timber.data
scoreboard players operation #pz timber.data = #hw timber.data
scoreboard players operation #pz timber.data *= #cos timber.data
scoreboard players operation #pz timber.data /= #10000 timber.data
scoreboard players operation #pz timber.data += #fcz timber.data
