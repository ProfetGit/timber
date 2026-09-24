execute if score #hop timber.data matches 1 store result storage timber:anim it.Motion[0] double 0.001 run random value -60..60
execute if score #hop timber.data matches 1 store result storage timber:anim it.Motion[1] double 0.001 run random value 200..260
execute if score #hop timber.data matches 1 store result storage timber:anim it.Motion[2] double 0.001 run random value -60..60
execute if score #hop timber.data matches 1 run return 0
execute store result storage timber:anim it.Motion[0] double 0.001 run random value -140..140
execute store result storage timber:anim it.Motion[1] double 0.001 run random value 180..320
execute store result storage timber:anim it.Motion[2] double 0.001 run random value -140..140
