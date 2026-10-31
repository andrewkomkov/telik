/* Telik is a pairing server only, so it does not need the fruit client of pair_ap or its plist use. */
#include <stdint.h>
#include <stddef.h>
#include <sys/types.h>

#include "pair.h"
#include "pair-internal.h"

struct pair_definition pair_client_fruit = { 0 };
