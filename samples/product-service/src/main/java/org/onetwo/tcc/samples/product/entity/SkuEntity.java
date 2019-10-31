
package org.onetwo.tcc.samples.product.entity;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.SafeHtml;
import org.onetwo.dbm.jpa.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/***
 * 商品表
 */
@SuppressWarnings("serial")
@Entity
@Table(name="prd_sku")
@Data
@EqualsAndHashCode(callSuper=true)
public class SkuEntity extends BaseEntity  {

    @Id
/*    @GeneratedValue(strategy = GenerationType.AUTO, generator="snowflake") 
    @DbmIdGenerator(name="snowflake", generatorClass=SnowflakeGenerator.class)*/
    @NotNull
    Long id;
    
    /***
     * 剩余库存数量
     */
    @NotNull
    Integer stockCount;
    
    /***
     * 冻结库存数量
     */
    @NotNull
    Integer frozenStockCount;
    
    /***
     * 商品名称
     */
    @NotNull
    @NotBlank
    @Length(max=100)
    @SafeHtml
    String name;
    
}