
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto; --me hace falta para crear índices como UUID y no integers

-- Crear la tabla con UUID como PK (así se alinea con la manera en la que SpringAI lo hace por defecto)
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content text,        
    metadata jsonb,      
    embedding vector(1536)
);

-- Índice para acelerar búsquedas de similitud
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store
    USING ivfflat (embedding vector_l2_ops)  
    WITH (lists = 100);
    
--RESPECTO AL IVVFLAT:    
--El ivfflat = IVF + Flat = Inversed file + Flat = Dividir la información en clústers + Vectores planos, sin comprimir
--Otras opciones: 
--Flat = sin hacer clusters, consultar uno por uno (bases pequeñas)
--ivfpq = además de clusters usa quantization (para bases gigantescas)

 -- lists = cuántos clústers queremos hacer
    
    
--RESPECTO AL EMBEDDING:
 -- Estoy usando L2 (distancia euclídea porque tiene en cuenta la magnitud y la distancia de los embeddings en la base de datos, y como no estoy normalizando, me interesa)
 --Las otras opciones son IP (Internal Product) y Cos (Coseno). 
 --El producto interno es el de toda la vida: a · b = |a| |b| cosθ. Si los vectores están normalizados, el coseno y el producto interno son iguales.
 
 --Hay que tener en cuenta tres características a la hora de hacer búsquedas de similitud con nuestros vectores: Magnitud, Alineación y Distancia.
 --La magnitud es la "cantidad de contenido"
 --La alineación es "a dónde apuntan, la dirección del contenido". La dirección del contenido indica relación conceptual. Dos vectores que apuntan en la misma dirección tratan conceptos similares independientemente de su magnitud.
 --La distancia combina la magnitud y la dirección para ver qué tan lejos está un contenido de otro (la relevancia y la correlación).
 
 --Resumiendo: L2 (vector_l2_ops) usa distancia euclidea, IP (vector_ip_ops) usa dirección + magnitud también, y el Cos (vector_cos_ops) sólo utiliza la dirección (se usa con vectores normalizados porque la magnitud de todos es la misma).
 
