-- Conta as linhas de cada tabela. Só lê; não altera nada.
-- Rode no banco de casa e no Aiven e compare os números.
select table_name as tabela,
       (xpath('/row/c/text()',
              query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text as linhas
from information_schema.tables
where table_schema = 'public' and table_type = 'BASE TABLE'
order by 1;
