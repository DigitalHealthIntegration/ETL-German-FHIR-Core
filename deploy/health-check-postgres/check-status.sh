#!/bin/sh

if psql -U ohdsi_admin_user -d ohdsi -c "SELECT * FROM cds_cdm.load_status;" | grep -q "started"; then
    echo "Table exists, returning healthy"
    exit 0
else
    echo "Table does not exist, returning not healthy"
    exit 1
fi

