-- Lightweight colour prototypes for the opt-in local image-search demo.
-- Values are the RGB background colours of the six bundled catalogue illustrations.
UPDATE products SET embedding = CASE name
    WHEN 'Áo thun Everyday' THEN '[232,229,220]'::jsonb
    WHEN 'Sơ mi Linen' THEN '[222,223,211]'::jsonb
    WHEN 'Quần Wide Leg' THEN '[229,227,218]'::jsonb
    WHEN 'Váy Midi Flow' THEN '[232,223,215]'::jsonb
    WHEN 'Áo Knit Essential' THEN '[222,223,212]'::jsonb
    WHEN 'Quần Short Weekend' THEN '[230,227,216]'::jsonb
    ELSE embedding
END
WHERE embedding IS NULL
  AND name IN ('Áo thun Everyday','Sơ mi Linen','Quần Wide Leg','Váy Midi Flow','Áo Knit Essential','Quần Short Weekend');
