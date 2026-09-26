# Taylor series to x^6 for |x| up to about 1.2 radians (#t1, x10000) -> #s1 #c1 (x10000)
scoreboard players operation #t2 timber.data = #t1 timber.data
scoreboard players operation #t2 timber.data *= #t1 timber.data
scoreboard players operation #t2 timber.data /= #10000 timber.data
scoreboard players operation #t3 timber.data = #t2 timber.data
scoreboard players operation #t3 timber.data *= #t1 timber.data
scoreboard players operation #t3 timber.data /= #10000 timber.data
scoreboard players operation #t4 timber.data = #t2 timber.data
scoreboard players operation #t4 timber.data *= #t2 timber.data
scoreboard players operation #t4 timber.data /= #10000 timber.data
scoreboard players operation #t5 timber.data = #t4 timber.data
scoreboard players operation #t5 timber.data *= #t1 timber.data
scoreboard players operation #t5 timber.data /= #10000 timber.data
scoreboard players operation #t6 timber.data = #t4 timber.data
scoreboard players operation #t6 timber.data *= #t2 timber.data
scoreboard players operation #t6 timber.data /= #10000 timber.data
scoreboard players operation #s1 timber.data = #t1 timber.data
scoreboard players operation #t3 timber.data /= #6 timber.data
scoreboard players operation #s1 timber.data -= #t3 timber.data
scoreboard players operation #t5 timber.data /= #120 timber.data
scoreboard players operation #s1 timber.data += #t5 timber.data
scoreboard players set #c1 timber.data 10000
scoreboard players operation #t2 timber.data /= #2 timber.data
scoreboard players operation #c1 timber.data -= #t2 timber.data
scoreboard players operation #t4 timber.data /= #24 timber.data
scoreboard players operation #c1 timber.data += #t4 timber.data
scoreboard players operation #t6 timber.data /= #720 timber.data
scoreboard players operation #c1 timber.data -= #t6 timber.data
